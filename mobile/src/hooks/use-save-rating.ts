import { useMutation, useQueryClient } from "@tanstack/react-query";

import { ratingsApi, type DeleteRatingInput, type SaveRatingInput } from "@/api/ratings-api";

import { dayRatingsKey } from "./use-day-ratings";

export function useSaveRating(date: string) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: dayRatingsKey(date) });

  const save = useMutation({
    mutationFn: (input: SaveRatingInput) => ratingsApi.save(input),
    onSettled: refresh,
  });
  const remove = useMutation({
    mutationFn: (input: DeleteRatingInput) => ratingsApi.remove(input),
    onSettled: refresh,
  });

  return { save, remove };
}
